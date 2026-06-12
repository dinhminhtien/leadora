"use client";

import React, { useState, useMemo } from "react";
import { Star, ThumbsUp, Search } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { mockDb, type CustomerFeedback } from "@/shared/mock/mockData";

export function CustomerFeedbackListScreen() {
  const [feedbacks, setFeedbacks] = useState<CustomerFeedback[]>(mockDb.feedbacks);
  const [search, setSearch] = useState("");

  const filteredFeedbacks = useMemo(() => {
    return feedbacks.filter(
      f =>
        f.guestName.toLowerCase().includes(search.toLowerCase()) ||
        f.comment.toLowerCase().includes(search.toLowerCase()) ||
        f.category.toLowerCase().includes(search.toLowerCase())
    );
  }, [feedbacks, search]);

  const stats = useMemo(() => {
    const total = feedbacks.length;
    const avg = feedbacks.reduce((sum, f) => sum + f.rating, 0) / (total || 1);
    const positive = feedbacks.filter(f => f.rating >= 4).length;
    return {
      total,
      avgRating: avg.toFixed(1),
      positivePct: ((positive / (total || 1)) * 100).toFixed(0)
    };
  }, [feedbacks]);

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Guest Experience Feedback</h1>
          <p className="text-xs text-slate-400">Track catering reviews, banquet feedback, lodging Net Promoter Score (NPS)</p>
        </div>
        <Badge variant="success" className="text-xs px-2.5 font-bold uppercase bg-emerald-100 text-emerald-800">
          NPS Target 95%
        </Badge>
      </div>

      {/* NPS Statistics ribbon */}
      <div className="grid grid-cols-3 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-xs">
        <div className="border-r border-slate-100 last:border-0 pr-4">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">Total Reviews</span>
          <span className="text-lg font-bold text-slate-800 block mt-1">{stats.total} guest reviews</span>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">Average Star Rating</span>
          <span className="text-lg font-bold text-slate-800 mt-1 flex items-center gap-1">
            <Star className="size-4 text-amber-400 fill-amber-400" />
            {stats.avgRating} / 5.0
          </span>
        </div>
        <div className="px-4">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">Positive Sentiment Rate</span>
          <span className="text-lg font-bold text-blue-600 mt-1 flex items-center gap-1">
            <ThumbsUp className="size-4 text-blue-500" />
            {stats.positivePct}% Excellent
          </span>
        </div>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search comments, guests, category..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Guest Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Category</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Stars Rating</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Guest Commentary</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Review Date</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Sentiment</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredFeedbacks.length > 0 ? (
              filteredFeedbacks.map(f => (
                <TableRow key={f.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">{f.guestName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-700 font-semibold">{f.category}</TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    <div className="inline-flex items-center gap-0.5 text-xs text-slate-700 font-bold">
                      <Star className="size-3 text-amber-400 fill-amber-400" />
                      {f.rating}.0
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-medium italic max-w-sm leading-relaxed">
                    "{f.comment}"
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-400">{f.date}</TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant={f.rating >= 4 ? "success" : f.rating === 3 ? "warning" : "danger"} size="sm" className="font-bold text-[9px] uppercase">
                      {f.rating >= 4 ? "POSITIVE" : f.rating === 3 ? "NEUTRAL" : "DETRACTOR"}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={6} className="py-8 text-center text-slate-400 text-xs">
                  No feedback comments matched your search criteria.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
